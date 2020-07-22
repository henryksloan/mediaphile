import {Component, ElementRef, OnInit, ViewChild} from '@angular/core';
import {InfoService} from "../../info.service";
import {LoginStatus} from "../../auth/login.status";
import {ActivatedRoute} from "@angular/router";
import {Title} from "@angular/platform-browser";
import {Observable} from "rxjs";
import {faMinusCircle} from "@fortawesome/free-solid-svg-icons";
import {NgbModal} from "@ng-bootstrap/ng-bootstrap";
import {ModalComponent} from "../helper/modal/modal.component";

@Component({
  selector: 'app-book-details',
  templateUrl: './book-details.component.html',
  styleUrls: ['./book-details.component.scss']
})
export class BookDetailsComponent implements OnInit {


  public miniusCircle = faMinusCircle;

  public userId: string;
  public bookId: string;

  public entity: Observable<any>;
  public bookData: {};

  public queue: Object[];
  public hasQueue: boolean;

  public watched: Object[];
  public hasWatched: boolean;

  public isLoggedIn: boolean;
  public isInQueue: boolean;
  public isInWatched: boolean;

  constructor(private infoSvc: InfoService, public loginStatus: LoginStatus, private route: ActivatedRoute, private title: Title, private modalService: NgbModal) { }

  ngOnInit(): void {
    this.loginStatus.sharedAccountId.subscribe(x => {
      this.userId = x;
      if(x != "") {
        this.infoSvc.getQueue(this.userId, "queue").subscribe(data => {
          this.queue = data;
          this.hasQueue = true;
          this.isInQueue = this.isItemInList(data, this.bookId);
        })
        this.infoSvc.getQueue(this.userId, "viewed").subscribe(data => {
          this.watched = data;
          this.hasWatched = true;
          this.isInWatched = this.isItemInList(data, this.bookId);
        })
      }
    })

    this.loginStatus.sharedStatus.subscribe(status => {
      this.isLoggedIn = status;
    })

    if (this.route.snapshot.paramMap.get("id") != undefined) {
      this.bookId = this.route.snapshot.paramMap.get("id");
    }
    if(this.bookId != null){
      this.entity = this.infoSvc.getBookDetails(this.bookId);
      this.entity.subscribe(data => {
        this.bookData = data;
        this.title.setTitle(`Mediaphile Listing for "${data['volumeInfo']["title"]}"`)
      });
    }
  }

  public getEntityImageUrl() : string {
    return "assets/placeholder.jpeg"
  }

  public getEntityPosterUrl(): string {
    if("imageLinks" in this.bookData["volumeInfo"]) {
      return this.bookData["volumeInfo"]["imageLinks"]["thumbnail"]
    }
    return "assets/poster-placeholder.png"
  }


  public addToQueuedList() {
    this.infoSvc.postQueue(
      this.getEntityPosterUrl(),
      this.bookId,
      "book",
      this.bookData["volumeInfo"]["title"],
      "queue",
      this.userId
    ).subscribe(x => {
      this.isInQueue = true;
      if(x["success"]) {
        this.showMessage("Success!", "Successfully added to queue!");
      }
    }, error => {
      this.showMessage("Oops!", "Unable to add book to queue, try again later!");
    })
  }

  public addToWatchedList() {
    this.infoSvc.postQueue(
      this.getEntityPosterUrl(),
      this.bookId,
      "book",
      this.bookData["volumeInfo"]["title"],
      "viewed",
      this.userId
    ).subscribe(x => {
      this.isInWatched = true;
      if(x["success"]) {
        this.showMessage("Success!", "Successfully added to read list!");
      }
    }, error => {
      this.showMessage("Oops!", "Unable to add book to read list, try again later!");
    })
  }

  public removeFromList(listType: string) {
    this.infoSvc.deleteFromQueue(listType, "book", this.bookId).subscribe(data => {
      if(listType == "queue") {
        this.isInQueue = false;
      } else {
        this.isInWatched = false;
      }
      this.showMessage("Success!", "Deleted book successfully!");
    }, error => {
      this.showMessage("Oops!", "Unable to delete book from list, try again later!");
    })
  }

  public isItemInList(list: {}[], mediaId: string) {
    return (list.some(function(el) {
      return el["mediaId"] === mediaId;
    }));
  }

  scroll(el: HTMLElement) {
    el.scrollIntoView();
  }

  public showMessage(title: string, message: string) {
    const modalRef = this.modalService.open(ModalComponent);
    modalRef.componentInstance.title = title
    modalRef.componentInstance.message = message
  }
}